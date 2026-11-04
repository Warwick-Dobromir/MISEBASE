package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.InventoryMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.InventoryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 库存 Repository 基础实现（阶段 2）。
 *
 * `appendIfAbsent` 的顺序：查幂等键 → 校验余额 → 原子调整批次 → 追加流水。
 * 流水只追加；调整失败时不写流水，调用方须把失败视为"整笔未发生"。
 */
@Suppress("TooGenericExceptionCaught")
class InventoryRepositoryImpl
    @Inject
    constructor(
        private val batchDao: InventoryBatchDao,
        private val ledgerDao: InventoryLedgerDao,
        private val unitDao: UnitDao,
        private val clock: Clock,
    ) : InventoryRepository {
        override suspend fun listItems(): Outcome<List<InventoryItem>> {
            return try {
                val items =
                    batchDao.listActive().map { batch ->
                        val unit = loadUnit(batch.stockUnitId) ?: return notFoundUnit(batch.stockUnitId)
                        InventoryMapper.toDomain(batch, unit)
                    }
                Outcome.success(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
        }

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> {
            return try {
                val entity = batchDao.findById(batchId.value) ?: return Outcome.success(null)
                val unit = loadUnit(entity.stockUnitId) ?: return notFoundUnit(entity.stockUnitId)
                Outcome.success(InventoryMapper.toDomain(entity, unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
        }

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> {
            return try {
                val entity = ledgerDao.findByIdempotencyKey(key) ?: return Outcome.success(null)
                val unit = loadUnit(entity.stockUnitId) ?: return notFoundUnit(entity.stockUnitId)
                Outcome.success(InventoryMapper.toDomain(entity, unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
        }

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> =
            try {
                if (ledgerDao.findByIdempotencyKey(entry.idempotencyKey) != null) {
                    Outcome.success(false)
                } else {
                    applyNewEntry(entry)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        private suspend fun applyNewEntry(entry: InventoryLedgerEntry): Outcome<Boolean> {
            val batch =
                batchDao.findById(entry.batchId.value)
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = entry.batchId.value),
                    )
            if (batch.quantityOnHand + entry.onHandDelta < -EPSILON) {
                return insufficient(entry, requested = -entry.onHandDelta, available = batch.quantityOnHand)
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
            ledgerDao.insert(InventoryMapper.toEntity(entry))
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> {
            return try {
                val entries =
                    ledgerDao.listAll().map { ledger ->
                        val unit = loadUnit(ledger.stockUnitId) ?: return notFoundUnit(ledger.stockUnitId)
                        InventoryMapper.toDomain(ledger, unit)
                    }
                Outcome.success(entries)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
        }

        private suspend fun loadUnit(unitId: String): Unit? = unitDao.findById(unitId)?.let(UnitMapper::toDomain)

        private fun notFoundUnit(unitId: String): Outcome<Nothing> =
            Outcome.failure(CulinaryError.NotFound(entity = "unit", id = unitId))

        private fun insufficient(
            entry: InventoryLedgerEntry,
            requested: Double,
            available: Double,
        ): Outcome<Nothing> =
            Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = entry.productId.value,
                            requested = requested,
                            available = available,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )

        private companion object {
            const val EPSILON = 1e-9
        }
    }

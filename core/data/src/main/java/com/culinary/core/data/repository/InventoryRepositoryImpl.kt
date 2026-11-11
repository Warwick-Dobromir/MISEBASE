package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.InventoryMapper
import com.culinary.core.data.mapper.ProductMapper
import com.culinary.core.data.mapper.StorageLocationMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.StorageLocationDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.model.BatchStatusDb
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.rule.common.NameText
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 库存 Repository 的 Room 实现。
 *
 * `appendIfAbsent` 的顺序：查幂等键 → 读批次 → 校验可用量 → 原子调整批次 → 追加流水。
 * 流水只追加；调整失败时不写流水，调用方须把失败视为"整笔未发生"。
 * 方法本身不开事务，事务边界由 Use Case 通过 `TransactionRunner` 控制。
 */
@Suppress("TooGenericExceptionCaught")
class InventoryRepositoryImpl
    @Inject
    constructor(
        private val batchDao: InventoryBatchDao,
        private val ledgerDao: InventoryLedgerDao,
        private val productDao: ProductDao,
        private val locationDao: StorageLocationDao,
        private val unitDao: UnitDao,
        private val idGenerator: IdGenerator,
        private val clock: Clock,
    ) : InventoryRepository {
        override suspend fun listItems(): Outcome<List<InventoryItem>> =
            read {
                batchDao.listActive().mapNotNull { batch ->
                    val unit = loadUnit(batch.stockUnitId) ?: unitNotFound(batch.stockUnitId)
                    InventoryMapper.toDomain(batch, unit)
                }
            }

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> =
            read {
                val entity = batchDao.findById(batchId.value)
                if (entity == null) {
                    null
                } else {
                    val unit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
                    InventoryMapper.toDomain(entity, unit)
                }
            }

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            read {
                val entity = ledgerDao.findByIdempotencyKey(key) ?: return@read null
                val unit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
                InventoryMapper.toDomain(entity, unit)
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

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> =
            read {
                ledgerDao.listAll().map { ledger ->
                    val unit = loadUnit(ledger.stockUnitId) ?: unitNotFound(ledger.stockUnitId)
                    InventoryMapper.toDomain(ledger, unit)
                }
            }

        override suspend fun listProducts(): Outcome<List<Product>> =
            read {
                productDao.listActive().mapNotNull { entity ->
                    val stockUnit = loadUnit(entity.stockUnitId) ?: return@mapNotNull null
                    val contentUnit = loadUnit(entity.contentUnitId) ?: return@mapNotNull null
                    ProductMapper.toDomain(entity, stockUnit, contentUnit)
                }
            }

        override suspend fun findProduct(id: ProductId): Outcome<Product?> =
            read {
                val entity = productDao.findById(id.value) ?: return@read null
                val stockUnit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
                val contentUnit = loadUnit(entity.contentUnitId) ?: unitNotFound(entity.contentUnitId)
                ProductMapper.toDomain(entity, stockUnit, contentUnit)
            }

        override suspend fun saveProduct(product: Product): Outcome<ProductId> =
            read {
                val now = clock.now().toEpochMilli()
                productDao.insert(
                    ProductMapper.toEntity(
                        product,
                        normalizedName = NameText.normalize(product.name),
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                    ),
                )
                product.id
            }

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            read {
                val entity = productDao.findActiveByIngredient(ingredientId.value) ?: return@read null
                val stockUnit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
                val contentUnit = loadUnit(entity.contentUnitId) ?: unitNotFound(entity.contentUnitId)
                ProductMapper.toDomain(entity, stockUnit, contentUnit)
            }

        override suspend fun listLocations(): Outcome<List<StorageLocation>> =
            read { locationDao.listActive().map(StorageLocationMapper::toDomain) }

        override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> =
            read { locationDao.findById(id.value)?.let(StorageLocationMapper::toDomain) }

        override suspend fun saveLocation(location: StorageLocation): Outcome<LocationId> =
            read {
                val now = clock.now().toEpochMilli()
                locationDao.insert(
                    StorageLocationMapper.toEntity(
                        location,
                        normalizedName = NameText.normalize(location.name),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                location.id
            }

        override suspend fun ensureDefaultLocation(): Outcome<StorageLocation> =
            read {
                val existing = locationDao.firstActive()
                if (existing != null) {
                    StorageLocationMapper.toDomain(existing)
                } else {
                    val now = clock.now().toEpochMilli()
                    val location =
                        StorageLocation(
                            id = LocationId(idGenerator.newId()),
                            name = DEFAULT_LOCATION_NAME,
                        )
                    locationDao.insert(
                        StorageLocationMapper.toEntity(
                            location,
                            normalizedName = NameText.normalize(location.name),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    location
                }
            }

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> =
            read {
                val now = clock.now().toEpochMilli()
                val id = idGenerator.newId()
                batchDao.insert(
                    InventoryBatchEntity(
                        id = id,
                        productId = batch.productId.value,
                        locationId = batch.locationId.value,
                        batchCode = batch.batchCode,
                        receivedAt = batch.receivedAt.toEpochMilli(),
                        expiryDate = batch.expiresOn?.toString(),
                        quantityOnHand = 0.0,
                        quantityReserved = 0.0,
                        stockUnitId = batch.stockUnit.id.value,
                        status = BatchStatusDb.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                BatchId(id)
            }

        override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
            read {
                ledgerDao.listByBatch(batchId.value).map { ledger ->
                    val unit = loadUnit(ledger.stockUnitId) ?: unitNotFound(ledger.stockUnitId)
                    InventoryMapper.toDomain(ledger, unit)
                }
            }

        @Suppress("ReturnCount") // 余额/预留/并发三类守卫必须显式失败，合并会掩盖原因。
        private suspend fun applyNewEntry(entry: InventoryLedgerEntry): Outcome<Boolean> {
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
                    entry,
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
                InventoryMapper.toEntity(
                    entry.copy(onHandAfter = newOnHand, reservedAfter = newReserved),
                ),
            )
            return Outcome.success(true)
        }

        private suspend fun loadUnit(unitId: String): Unit? = unitDao.findById(unitId)?.let(UnitMapper::toDomain)

        private fun unitNotFound(unitId: String): Nothing = throw NoSuchElementException("missing unit: $unitId")

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

        private companion object {
            const val EPSILON = 1e-9
            const val DEFAULT_LOCATION_NAME = "默认储藏"
        }
    }

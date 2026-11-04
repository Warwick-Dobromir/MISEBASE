package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.StorageLocation

/**
 * 库存读写端口（MB-D-014）。
 *
 * - 余额来自批次物化字段，流水只追加；
 * - [appendIfAbsent] 必须原子地"查幂等键 → 写流水 → 调整批次余额"，
 *   返回 `false` 表示该幂等键已存在、本次未写入；
 * - 不提供直接改余额或删除流水的方法；
 * - Stage 4 增补商品/位置读取与批次创建，均为原子片段，事务边界仍在 Use Case。
 */
interface InventoryRepository {
    suspend fun listItems(): Outcome<List<InventoryItem>>

    suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?>

    suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?>

    suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean>

    suspend fun ledger(): Outcome<List<InventoryLedgerEntry>>

    /** 按商品读取启用商品；总览与表单共用。 */
    suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(emptyList())

    suspend fun findProduct(id: ProductId): Outcome<Product?> = Outcome.success(null)

    /** 新建库存商品（入库时从食材创建）。 */
    suspend fun saveProduct(product: Product): Outcome<ProductId> = Outcome.success(product.id)

    /** 按食材查找第一个启用商品；没有时返回 null。 */
    suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> = Outcome.success(null)

    suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(emptyList())

    suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> = Outcome.success(null)

    suspend fun saveLocation(location: StorageLocation): Outcome<LocationId> = Outcome.success(location.id)

    /** 确保至少有一个可用位置；实现必须幂等，供入库表单零配置使用。 */
    suspend fun ensureDefaultLocation(): Outcome<StorageLocation> =
        Outcome.success(StorageLocation(LocationId("default"), "默认储藏"))

    /** 创建余额为 0 的批次；首笔入库流水随后追加。 */
    suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> =
        Outcome.failure(
            com.culinary.core.common.CulinaryError
                .Unexpected("createBatch is not implemented"),
        )

    suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> = Outcome.success(emptyList())
}

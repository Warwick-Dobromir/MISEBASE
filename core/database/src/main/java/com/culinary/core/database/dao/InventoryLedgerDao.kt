package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryLedgerEntity

/**
 * 库存流水读写。
 *
 * 只暴露 `insert` 与查询：UPDATE/DELETE 在数据库层被触发器拒绝，
 * 这里不提供方法，避免业务代码误以为可以修改流水。
 */
@Dao
interface InventoryLedgerDao {
    @Insert
    suspend fun insert(entity: InventoryLedgerEntity)

    @Query("SELECT * FROM inventory_ledger WHERE batch_id = :batchId ORDER BY occurred_at")
    suspend fun listByBatch(batchId: String): List<InventoryLedgerEntity>

    @Query("SELECT * FROM inventory_ledger WHERE idempotency_key = :key")
    suspend fun findByIdempotencyKey(key: String): InventoryLedgerEntity?

    @Query("SELECT * FROM inventory_ledger ORDER BY occurred_at, id")
    suspend fun listAll(): List<InventoryLedgerEntity>

    @Query("SELECT COUNT(*) FROM inventory_ledger")
    suspend fun count(): Int
}

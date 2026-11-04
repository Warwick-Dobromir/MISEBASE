package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryBatchEntity

/** 库存批次读写。 */
@Dao
interface InventoryBatchDao {
    @Insert
    suspend fun insert(entity: InventoryBatchEntity)

    @Query("SELECT * FROM inventory_batch WHERE id = :id")
    suspend fun findById(id: String): InventoryBatchEntity?

    @Query("SELECT * FROM inventory_batch WHERE product_id = :productId ORDER BY received_at")
    suspend fun listByProduct(productId: String): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batch WHERE status = 'ACTIVE' ORDER BY product_id, expiry_date, id")
    suspend fun listActive(): List<InventoryBatchEntity>

    /**
     * 原子调整余额：只有调整后余额非负、且预留不超过在手量时才更新。
     * 返回 0 表示并发冲突或约束不满足，调用方必须视为失败，不得重试半条流水。
     */
    @Query(
        "UPDATE inventory_batch SET " +
            "quantity_on_hand = quantity_on_hand + :onHandDelta, " +
            "quantity_reserved = quantity_reserved + :reservedDelta, " +
            "updated_at = :updatedAt " +
            "WHERE id = :id " +
            "AND quantity_on_hand + :onHandDelta >= 0 " +
            "AND quantity_reserved + :reservedDelta >= 0 " +
            "AND quantity_reserved + :reservedDelta <= quantity_on_hand + :onHandDelta",
    )
    suspend fun applyDeltas(
        id: String,
        onHandDelta: Double,
        reservedDelta: Double,
        updatedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM inventory_batch")
    suspend fun count(): Int

    @Query("DELETE FROM inventory_batch WHERE id = :id")
    suspend fun deleteById(id: String)
}

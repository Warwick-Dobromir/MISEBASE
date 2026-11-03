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

    @Query("SELECT COUNT(*) FROM inventory_batch")
    suspend fun count(): Int

    @Query("DELETE FROM inventory_batch WHERE id = :id")
    suspend fun deleteById(id: String)
}

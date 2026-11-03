package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.PurchaseOrderItemEntity

/** 采购明细读写。 */
@Dao
interface PurchaseOrderItemDao {
    @Insert
    suspend fun insert(entity: PurchaseOrderItemEntity)

    @Query("SELECT * FROM purchase_order_item WHERE purchase_order_id = :orderId")
    suspend fun listByOrder(orderId: String): List<PurchaseOrderItemEntity>

    @Query("SELECT COUNT(*) FROM purchase_order_item")
    suspend fun count(): Int

    @Query("DELETE FROM purchase_order_item WHERE id = :id")
    suspend fun deleteById(id: String)
}

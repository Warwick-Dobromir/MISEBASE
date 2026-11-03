package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.PurchaseOrderEntity

/** 采购单读写。 */
@Dao
interface PurchaseOrderDao {
    @Insert
    suspend fun insert(entity: PurchaseOrderEntity)

    @Query("SELECT * FROM purchase_order WHERE id = :id")
    suspend fun findById(id: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_order WHERE code = :code")
    suspend fun findByCode(code: String): PurchaseOrderEntity?

    @Query("SELECT COUNT(*) FROM purchase_order")
    suspend fun count(): Int

    @Query("DELETE FROM purchase_order WHERE id = :id")
    suspend fun deleteById(id: String)
}

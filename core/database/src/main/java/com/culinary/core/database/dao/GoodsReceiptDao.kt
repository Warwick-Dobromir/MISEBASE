package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.GoodsReceiptEntity

/** 收货单读写。 */
@Dao
interface GoodsReceiptDao {
    @Insert
    suspend fun insert(entity: GoodsReceiptEntity)

    @Query("SELECT * FROM goods_receipt WHERE id = :id")
    suspend fun findById(id: String): GoodsReceiptEntity?

    @Query("SELECT COUNT(*) FROM goods_receipt")
    suspend fun count(): Int

    @Query("DELETE FROM goods_receipt WHERE id = :id")
    suspend fun deleteById(id: String)
}

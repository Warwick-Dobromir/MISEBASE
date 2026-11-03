package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.GoodsReceiptItemEntity

/** 收货明细读写。 */
@Dao
interface GoodsReceiptItemDao {
    @Insert
    suspend fun insert(entity: GoodsReceiptItemEntity)

    @Query("SELECT * FROM goods_receipt_item WHERE goods_receipt_id = :receiptId")
    suspend fun listByReceipt(receiptId: String): List<GoodsReceiptItemEntity>

    @Query("SELECT COUNT(*) FROM goods_receipt_item")
    suspend fun count(): Int
}

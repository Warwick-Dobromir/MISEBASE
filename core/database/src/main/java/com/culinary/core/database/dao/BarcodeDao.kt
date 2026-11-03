package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.BarcodeEntity

/** 条码读写。 */
@Dao
interface BarcodeDao {
    @Insert
    suspend fun insert(entity: BarcodeEntity)

    @Query("SELECT * FROM barcode WHERE code = :code")
    suspend fun findByCode(code: String): BarcodeEntity?

    @Query("SELECT * FROM barcode WHERE product_id = :productId")
    suspend fun listByProduct(productId: String): List<BarcodeEntity>

    @Query("SELECT COUNT(*) FROM barcode")
    suspend fun count(): Int
}

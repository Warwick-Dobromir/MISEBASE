package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.StorageLocationEntity

/** 储存位置读写。 */
@Dao
interface StorageLocationDao {
    @Insert
    suspend fun insert(entity: StorageLocationEntity)

    /** 按主键整行更新；调用方须保留原 `created_at`（v9 库位编辑）。 */
    @Update
    suspend fun update(entity: StorageLocationEntity): Int

    @Query("SELECT * FROM storage_location WHERE id = :id")
    suspend fun findById(id: String): StorageLocationEntity?

    @Query("SELECT * FROM storage_location WHERE status = 'ACTIVE' ORDER BY sort_order, normalized_name, id")
    suspend fun listActive(): List<StorageLocationEntity>

    @Query("SELECT * FROM storage_location WHERE status = 'ACTIVE' ORDER BY sort_order, normalized_name, id LIMIT 1")
    suspend fun firstActive(): StorageLocationEntity?

    @Query("SELECT COUNT(*) FROM storage_location")
    suspend fun count(): Int

    @Query("DELETE FROM storage_location WHERE id = :id")
    suspend fun deleteById(id: String)
}

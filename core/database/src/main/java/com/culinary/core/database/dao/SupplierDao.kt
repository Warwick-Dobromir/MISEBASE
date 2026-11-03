package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.SupplierEntity

/** 供应商读写。 */
@Dao
interface SupplierDao {
    @Insert
    suspend fun insert(entity: SupplierEntity)

    @Query("SELECT * FROM supplier WHERE id = :id")
    suspend fun findById(id: String): SupplierEntity?

    @Query("SELECT COUNT(*) FROM supplier")
    suspend fun count(): Int

    @Query("DELETE FROM supplier WHERE id = :id")
    suspend fun deleteById(id: String)
}

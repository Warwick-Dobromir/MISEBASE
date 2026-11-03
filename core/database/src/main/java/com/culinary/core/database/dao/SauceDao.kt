package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.SauceEntity

/** 酱汁档案读写。 */
@Dao
interface SauceDao {
    @Insert
    suspend fun insert(entity: SauceEntity)

    @Query("SELECT * FROM sauce WHERE id = :id")
    suspend fun findById(id: String): SauceEntity?

    @Query("SELECT COUNT(*) FROM sauce")
    suspend fun count(): Int

    @Query("DELETE FROM sauce WHERE id = :id")
    suspend fun deleteById(id: String)
}

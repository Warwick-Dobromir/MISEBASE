package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.SauceComponentEntity

/** 酱汁组成行读写。 */
@Dao
interface SauceComponentDao {
    @Insert
    suspend fun insert(entity: SauceComponentEntity)

    @Insert
    suspend fun insertAll(entities: List<SauceComponentEntity>)

    @Query("SELECT * FROM sauce_component WHERE sauce_version_id = :versionId ORDER BY line_no")
    suspend fun listByVersion(versionId: String): List<SauceComponentEntity>

    @Query("SELECT COUNT(*) FROM sauce_component")
    suspend fun count(): Int
}

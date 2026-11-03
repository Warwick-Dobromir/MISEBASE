package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.culinary.core.database.entity.AppMetadataEntity

/** 应用元数据读写；同键写入覆盖旧值。 */
@Dao
interface AppMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AppMetadataEntity)

    @Query("SELECT * FROM app_metadata WHERE key = :key")
    suspend fun find(key: String): AppMetadataEntity?

    @Query("SELECT COUNT(*) FROM app_metadata")
    suspend fun count(): Int
}

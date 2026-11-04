package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.SauceEntity
import kotlinx.coroutines.flow.Flow

/** 酱汁档案读写。 */
@Dao
interface SauceDao {
    @Insert
    suspend fun insert(entity: SauceEntity)

    @Update
    suspend fun update(entity: SauceEntity)

    @Query("SELECT * FROM sauce WHERE id = :id")
    suspend fun findById(id: String): SauceEntity?

    @Query("SELECT * FROM sauce WHERE deleted_at IS NULL AND status = 'ACTIVE' ORDER BY normalized_name, id")
    suspend fun listActive(): List<SauceEntity>

    @Query("SELECT * FROM sauce WHERE deleted_at IS NULL AND status = 'ACTIVE' ORDER BY normalized_name, id")
    fun observeActive(): Flow<List<SauceEntity>>

    @Query("SELECT * FROM sauce WHERE normalized_name = :normalizedName AND deleted_at IS NULL")
    suspend fun findActiveByNormalizedName(normalizedName: String): SauceEntity?

    @Query(
        "UPDATE sauce SET status = 'ARCHIVED', updated_at = :at, revision = revision + 1 WHERE id = :id",
    )
    suspend fun archive(
        id: String,
        at: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM sauce")
    suspend fun count(): Int

    @Query("DELETE FROM sauce WHERE id = :id")
    suspend fun deleteById(id: String)
}

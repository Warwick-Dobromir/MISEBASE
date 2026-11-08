package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

/** 菜谱档案读写。 */
@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(entity: RecipeEntity)

    @Update
    suspend fun update(entity: RecipeEntity)

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun findById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe WHERE deleted_at IS NULL AND status = 'ACTIVE' ORDER BY normalized_name, id")
    suspend fun listActive(): List<RecipeEntity>

    @Query("SELECT * FROM recipe WHERE deleted_at IS NULL AND status = 'ACTIVE' ORDER BY normalized_name, id")
    fun observeActive(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE normalized_name = :normalizedName AND deleted_at IS NULL")
    suspend fun findActiveByNormalizedName(normalizedName: String): RecipeEntity?

    @Query(
        "UPDATE recipe SET status = 'ARCHIVED', updated_at = :at, revision = revision + 1 WHERE id = :id",
    )
    suspend fun archive(
        id: String,
        at: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM recipe")
    suspend fun count(): Int

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recipe WHERE seed_version = :seedVersion")
    suspend fun deleteSeed(seedVersion: String): Int

    @Query("DELETE FROM recipe WHERE seed_version IS NOT NULL")
    suspend fun deleteAllSeed(): Int

    @Query("SELECT COUNT(*) FROM recipe WHERE seed_version = :seedVersion")
    suspend fun countSeed(seedVersion: String): Int
}

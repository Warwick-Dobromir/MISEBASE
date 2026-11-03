package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.IngredientEntity

/** 食材读写；软删除是业务默认，硬删除只用于测试与维护。 */
@Dao
interface IngredientDao {
    @Insert
    suspend fun insert(entity: IngredientEntity)

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun findById(id: String): IngredientEntity?

    @Query("SELECT * FROM ingredient WHERE deleted_at IS NULL ORDER BY normalized_name")
    suspend fun allActive(): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE normalized_name = :normalizedName AND deleted_at IS NULL")
    suspend fun findActiveByNormalizedName(normalizedName: String): IngredientEntity?

    @Query(
        "UPDATE ingredient SET deleted_at = :at, updated_at = :at, revision = revision + 1 " +
            "WHERE id = :id",
    )
    suspend fun softDelete(
        id: String,
        at: Long,
    )

    @Query("DELETE FROM ingredient WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM ingredient")
    suspend fun count(): Int
}

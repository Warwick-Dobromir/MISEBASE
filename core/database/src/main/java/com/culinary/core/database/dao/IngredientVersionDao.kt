package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.IngredientVersionEntity

/** 食材历史快照只追加、不覆盖。 */
@Dao
interface IngredientVersionDao {
    @Insert
    suspend fun insert(version: IngredientVersionEntity)

    @Query("SELECT * FROM ingredient_version WHERE ingredient_id = :ingredientId ORDER BY version_no DESC")
    suspend fun listByIngredient(ingredientId: String): List<IngredientVersionEntity>

    @Query("SELECT COALESCE(MAX(version_no), 0) FROM ingredient_version WHERE ingredient_id = :ingredientId")
    suspend fun maxVersionNo(ingredientId: String): Int
}

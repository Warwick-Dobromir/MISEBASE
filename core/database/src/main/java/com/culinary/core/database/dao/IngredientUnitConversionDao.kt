package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.IngredientUnitConversionEntity

/** 食材级换算读写。 */
@Dao
interface IngredientUnitConversionDao {
    @Insert
    suspend fun insert(entity: IngredientUnitConversionEntity)

    @Query("SELECT * FROM ingredient_unit_conversion WHERE ingredient_id = :ingredientId")
    suspend fun listByIngredient(ingredientId: String): List<IngredientUnitConversionEntity>

    @Query("SELECT COUNT(*) FROM ingredient_unit_conversion")
    suspend fun count(): Int
}

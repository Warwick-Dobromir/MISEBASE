package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.IngredientAliasEntity

/** 食材别名读写；别名全局唯一由唯一索引保证。 */
@Dao
interface IngredientAliasDao {
    @Insert
    suspend fun insert(entity: IngredientAliasEntity)

    @Query("SELECT * FROM ingredient_alias WHERE normalized_alias = :normalizedAlias")
    suspend fun findByNormalizedAlias(normalizedAlias: String): IngredientAliasEntity?

    @Query("SELECT * FROM ingredient_alias WHERE ingredient_id = :ingredientId ORDER BY normalized_alias")
    suspend fun listByIngredient(ingredientId: String): List<IngredientAliasEntity>

    @Query("SELECT COUNT(*) FROM ingredient_alias")
    suspend fun count(): Int
}

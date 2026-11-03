package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.RecipeVersionEntity

/** 菜谱版本读写。 */
@Dao
interface RecipeVersionDao {
    @Insert
    suspend fun insert(entity: RecipeVersionEntity)

    @Query("SELECT * FROM recipe_version WHERE id = :id")
    suspend fun findById(id: String): RecipeVersionEntity?

    @Query("SELECT * FROM recipe_version WHERE recipe_id = :recipeId AND is_current = 1")
    suspend fun findCurrent(recipeId: String): RecipeVersionEntity?

    @Query("UPDATE recipe_version SET status = 'PUBLISHED', published_at = :at WHERE id = :id")
    suspend fun publish(
        id: String,
        at: Long,
    )

    @Query("UPDATE recipe_version SET is_current = :current WHERE id = :id")
    suspend fun setCurrent(
        id: String,
        current: Boolean,
    )

    @Query("UPDATE recipe_version SET yield_qty = :qty WHERE id = :id")
    suspend fun updateYield(
        id: String,
        qty: Double,
    )

    @Query("SELECT COUNT(*) FROM recipe_version")
    suspend fun count(): Int
}

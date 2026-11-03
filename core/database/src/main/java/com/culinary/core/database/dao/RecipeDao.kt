package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.RecipeEntity

/** 菜谱档案读写。 */
@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(entity: RecipeEntity)

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun findById(id: String): RecipeEntity?

    @Query("SELECT COUNT(*) FROM recipe")
    suspend fun count(): Int

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteById(id: String)
}

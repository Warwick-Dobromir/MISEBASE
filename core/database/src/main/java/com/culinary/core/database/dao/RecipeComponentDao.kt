package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.RecipeComponentEntity

/** 菜谱组成行读写。 */
@Dao
interface RecipeComponentDao {
    @Insert
    suspend fun insert(entity: RecipeComponentEntity)

    @Insert
    suspend fun insertAll(entities: List<RecipeComponentEntity>)

    @Query("SELECT * FROM recipe_component WHERE recipe_version_id = :versionId ORDER BY line_no")
    suspend fun listByVersion(versionId: String): List<RecipeComponentEntity>

    @Query("DELETE FROM recipe_component WHERE recipe_version_id = :versionId")
    suspend fun deleteByVersion(versionId: String)

    @Query("SELECT COUNT(*) FROM recipe_component")
    suspend fun count(): Int
}
